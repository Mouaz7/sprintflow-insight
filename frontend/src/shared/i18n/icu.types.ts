export type IcuArguments = {
  projectCount: { count: number };
  storyCount: { count: number };
  sprintCount: { count: number };
  eventCount: { count: number };
  dueDate: { date: Date | number };
  emptyTaskCount: { count: number };
};

export type CompiledMessages = {
  [K in keyof IcuArguments]: (args: IcuArguments[K]) => string;
};
